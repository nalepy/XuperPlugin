package main

// probe_generic.go — replay a captured portalCore request with the app's EXACT TLS identity.
// env: REQ_URL, REQ_HEADERS (json map), REQ_BODY, H2_RAW=1 (raw h2 frames, lowercase headers).
import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	utls "github.com/refraction-networking/utls"
	"golang.org/x/net/http2"
	"golang.org/x/net/http2/hpack"
)

func buildSpec(host string) *utls.ClientHelloSpec {
	return &utls.ClientHelloSpec{
		TLSVersMin:         utls.VersionTLS10,
		TLSVersMax:         utls.VersionTLS12,
		CipherSuites:       []uint16{0xcca9, 0xcca8, 0xc02b, 0xc02f, 0xc02c, 0xc030, 0xc0ac, 0xc0ad, 0xc0ae, 0xc0af, 0xc023, 0xc027, 0xc024, 0xc028, 0xc009, 0xc013, 0xc00a, 0xc014, 0xc02d, 0xc031, 0xc02e, 0xc032, 0xc025, 0xc029, 0xc026, 0xc02a, 0xc004, 0xc00e, 0xc005, 0xc00f, 0x009c, 0x009d, 0xc09c, 0xc09d, 0xc0a0, 0xc0a1, 0x003c, 0x003d, 0x002f, 0x0035, 0xc008, 0xc012, 0xc003, 0xc00d, 0x000a, 0x0100},
		CompressionMethods: []byte{0x00},
		Extensions: []utls.TLSExtension{
			&utls.RenegotiationInfoExtension{Renegotiation: utls.RenegotiateNever, RenegotiatedConnection: []byte{}},
			&utls.SNIExtension{ServerName: host},
			&utls.SignatureAlgorithmsExtension{SupportedSignatureAlgorithms: []utls.SignatureScheme{0x0403, 0x0303, 0x0503, 0x0603, 0x0203, 0x0401, 0x0301, 0x0501, 0x0601, 0x0201}},
			&utls.SupportedCurvesExtension{Curves: []utls.CurveID{0x0017, 0x0018, 0x0019, 0x001d}},
			&utls.SupportedPointsExtension{SupportedPoints: []byte{0x00}},
			&utls.ALPNExtension{AlpnProtocols: []string{"h2", "http/1.1"}},
		},
	}
}

func dial(host string, ip string) (*utls.UConn, error) {
	target := host + ":443"
	if ip != "" {
		target = ip + ":443"
	}
	raw, err := net.DialTimeout("tcp", target, 20*time.Second)
	if err != nil {
		return nil, err
	}
	uconn := utls.UClient(raw, &utls.Config{ServerName: host, InsecureSkipVerify: true}, utls.HelloCustom)
	if err := uconn.ApplyPreset(buildSpec(host)); err != nil {
		return nil, err
	}
	if err := uconn.Handshake(); err != nil {
		return nil, err
	}
	return uconn, nil
}

func rawH2(uconn *utls.UConn, rawURL, body string, headers map[string]string) {
	u, _ := url.Parse(rawURL)
	// h2 preface + SETTINGS
	preface := "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n"
	var buf bytes.Buffer
	buf.WriteString(preface)
	settings := []byte{0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00} // SETTINGS len=0 type=4 flags=0 stream=0
	buf.Write(settings)
	// HPACK encode headers (lowercase like the app)
	var hb bytes.Buffer
	enc := hpack.NewEncoder(&hb)
	enc.WriteField(hpack.HeaderField{Name: ":method", Value: "POST"})
	enc.WriteField(hpack.HeaderField{Name: ":scheme", Value: "https"})
	enc.WriteField(hpack.HeaderField{Name: ":authority", Value: u.Host})
	enc.WriteField(hpack.HeaderField{Name: ":path", Value: u.RequestURI()})
	for k, v := range headers {
		enc.WriteField(hpack.HeaderField{Name: strings.ToLower(k), Value: v})
	}
	enc.WriteField(hpack.HeaderField{Name: "content-length", Value: fmt.Sprint(len(body))})
	hdrs := hb.Bytes()
	// HEADERS frame: len type=1 flags=0x4(END_HEADERS)|0x1(END_STREAM if no body) stream=1
	flags := byte(0x04)
	if len(body) == 0 {
		flags |= 0x01
	}
	hf := make([]byte, 9+len(hdrs))
	hf[0] = byte(len(hdrs) >> 16)
	hf[1] = byte(len(hdrs) >> 8)
	hf[2] = byte(len(hdrs))
	hf[3] = 0x01
	hf[4] = flags
	hf[5] = 0
	hf[6] = 0
	hf[7] = 0
	hf[8] = 1
	copy(hf[9:], hdrs)
	buf.Write(hf)
	if len(body) > 0 {
		// DATA frame with END_STREAM
		df := make([]byte, 9+len(body))
		df[0] = byte(len(body) >> 16)
		df[1] = byte(len(body) >> 8)
		df[2] = byte(len(body))
		df[3] = 0x00
		df[4] = 0x01 // END_STREAM
		df[8] = 1
		copy(df[9:], []byte(body))
		buf.Write(df)
	}
	uconn.SetDeadline(time.Now().Add(30 * time.Second))
	uconn.Write(buf.Bytes())
	// read response frames
	out := make([]byte, 65536)
	n, err := uconn.Read(out)
	if err != nil {
		fmt.Println("RAW READ ERR:", err)
		return
	}
	resp := out[:n]
	// extract headers + body heuristically (skip h2 frame headers)
	fmt.Println("RAW h2 response bytes:", n)
	// try to find JSON body
	idx := bytes.Index(resp, []byte("{"))
	if idx >= 0 {
		fmt.Println("RAW JSON tail:", string(resp[idx:idx+300]))
	}
	fmt.Println("RAW first 200:", string(resp[:min(200, n)]))
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

func main() {
	rawURL := os.Getenv("REQ_URL")
	if rawURL == "" {
		fmt.Println("REQ_URL required")
		return
	}
	body := os.Getenv("REQ_BODY")
	hdrs := map[string]string{}
	json.Unmarshal([]byte(os.Getenv("REQ_HEADERS")), &hdrs)
	u, _ := url.Parse(rawURL)
	host := u.Hostname()
	ip := os.Getenv("REQ_IP")

	uconn, err := dial(host, ip)
	if err != nil {
		fmt.Println("HANDSHAKE ERR:", err)
		return
	}
	cs := uconn.ConnectionState().CipherSuite
	ver := uconn.ConnectionState().Version
	alpn := uconn.ConnectionState().NegotiatedProtocol
	fmt.Printf("TLS OK version=%#x cipher=%#x alpn=%q\n", ver, cs, alpn)

	if os.Getenv("H2_RAW") == "1" {
		rawH2(uconn, rawURL, body, hdrs)
		return
	}

	tr := &http2.Transport{
		DialTLSContext: func(ctx context.Context, network, addr string, cfg *tls.Config) (net.Conn, error) {
			return uconn, nil
		},
	}
	client := &http.Client{Transport: tr, Timeout: 30 * time.Second}
	req, _ := http.NewRequest("POST", rawURL, bytes.NewBufferString(body))
	for k, v := range hdrs {
		req.Header.Set(k, v)
	}
	resp, err := client.Do(req)
	if err != nil {
		fmt.Println("H2 REQ ERR:", err)
		return
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(resp.Body)
	fmt.Println("H2 status:", resp.Status)
	for k, v := range resp.Header {
		fmt.Printf("   %s: %s\n", k, v)
	}
	fmt.Println("H2 body:", string(b))
	if strings.Contains(string(b), "portal200001") {
		fmt.Println("==> GATE: portal200001")
	} else if strings.Contains(string(b), "\"returnCode\":\"0\"") || strings.Contains(string(b), "\"returnCode\":0") {
		fmt.Println("==> ACCEPTED: returnCode 0 !!!")
	}
}
