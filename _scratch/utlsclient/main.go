package main

import (
	"bytes"
	"context"
	"crypto/tls"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"time"

	utls "github.com/refraction-networking/utls"
	"golang.org/x/net/http2"
)

const (
	KEY_B64 = "2b494e53756c664c2f44465245733572"
	HOST    = "emowvv.dqiswip4.xyz"
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

func main() {
	// VER override (env) tests the "version too low" hypothesis for portal200001
	appVer := os.Getenv("VER")
	if appVer == "" {
		appVer = "43405"
	}
	// session-31 lead: replay .4's STATIC b29/reserve1 with a FRESH userToken.
	// env overrides so the next agent can test in one command once a live token is pulled.
	tok := envOr("TOK", "94f1ace7-bb6b-4a79-ab0e-a2df4d5bcebe")
	uid := envOr("UID", "169355704")
	exp := envOr("EXP", "1785953097")
	// -------- build the request body exactly like the app --------
	body := `{"apkVersion":"` + appVer + `","appId":"com.android.msandroid","appLanguage":"es","b29":"4f6f786b4b5a7a3933666842554e6c55717338584b71325a3635436b4e463736583442714b345572434a504c556e72384136647252773d3d","contentType":"application/json;charset=utf-8","cpu":"armeabi-v7a","deviceToken":"","hardwareInfo":"sun50iw9p1","loginType":"2","model":"V76PRO","portalCode":"masnew","product":"walley","reserve1":"76356c476568424f4a38334761645a697957757344673d3d","sdkVer":29,"sn":"ca0e53edac957b8f6f187528933355f1","sysVersion":"2024-11-15 19:08:51_29_14.1_4.9.170","columnId":76182,"dataVersion":"pre34d022217-8b29-11f1-860c-e7ba14321033LiveDataV6","expireTimeStr":"` + exp + `","pageNum":1,"pageSize":3000,"userId":"` + uid + `","userToken":"` + tok + `"}`
	// encrypt: hex( base64( 3DES_ECB_PKCS5(plaintext) ) ) with key base64-decoded
	ct := threeDesECB([]byte(body))
	wire := toHexBase64(ct)

	dial := func() (*utls.UConn, error) {
		raw, err := net.DialTimeout("tcp", HOST+":443", 20*time.Second)
		if err != nil {
			return nil, err
		}
		uconn := utls.UClient(raw, &utls.Config{ServerName: HOST, InsecureSkipVerify: true}, utls.HelloCustom)
		if err := uconn.ApplyPreset(buildSpec(HOST)); err != nil {
			return nil, err
		}
		if err := uconn.Handshake(); err != nil {
			return nil, err
		}
		return uconn, nil
	}

	uconn, err := dial()
	if err != nil {
		fmt.Println("HANDSHAKE ERR:", err)
		return
	}
	cs := uconn.ConnectionState().CipherSuite
	ver := uconn.ConnectionState().Version
	alpn := uconn.ConnectionState().NegotiatedProtocol
	fmt.Printf("TLS OK version=%#x cipher=%#x alpn=%q\n", ver, cs, alpn)

	headers := map[string]string{
		"Content-type": "application/json;charset=utf-8",
		"apkVer":       appVer,
		"spkgVer":      "2024-11-15 19:08:51_29_14.1_4.9.170",
		"apk":          "com.android.msandroid",
	}

	if alpn == "h2" {
		tr := &http2.Transport{
			DialTLSContext: func(ctx context.Context, network, addr string, cfg *tls.Config) (net.Conn, error) {
				return uconn, nil
			},
		}
		client := &http.Client{Transport: tr, Timeout: 30 * time.Second}
		req, _ := http.NewRequest("POST", "https://"+HOST+"/api/portalCore/v6/getLiveData", bytes.NewBufferString(wire))
		for k, v := range headers {
			req.Header.Set(k, v)
		}
		// UA control: env UA overrides; empty string suppresses Go's default
		ua := os.Getenv("UA")
		req.Header.Set("User-Agent", ua)
		resp, err := client.Do(req)
		if err != nil {
			fmt.Println("H2 REQ ERR:", err)
			return
		}
		defer resp.Body.Close()
		b, _ := io.ReadAll(resp.Body)
		fmt.Println("H2 status:", resp.Status)
		fmt.Println("H2 resp headers:")
		for k, v := range resp.Header {
			fmt.Printf("   %s: %s\n", k, v)
		}
		fmt.Println("H2 body:", string(b))
	} else {
		req := "POST /api/portalCore/v6/getLiveData HTTP/1.1\r\nHost: " + HOST + "\r\nContent-type: application/json;charset=utf-8\r\napkVer: 43405\r\nspkgVer: 2024-11-15 19:08:51_29_14.1_4.9.170\r\napk: com.android.msandroid\r\nContent-Length: " + fmt.Sprint(len(wire)) + "\r\n\r\n" + wire
		uconn.SetDeadline(time.Now().Add(30 * time.Second))
		uconn.Write([]byte(req))
		buf := make([]byte, 65536)
		n, _ := uconn.Read(buf)
		fmt.Println("H1 response:", string(buf[:n])[:600])
	}
}

func envOr(k, def string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return def
}
