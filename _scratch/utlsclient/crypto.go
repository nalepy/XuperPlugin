package main

import (
	"crypto/des"
	"encoding/base64"
	"encoding/hex"
)

// hex( base64( 3DES-ECB-PKCS5(plain) ) ) with key = base64decode(KEY_B64)
func threeDesECB(plain []byte) []byte {
	key, _ := base64.StdEncoding.DecodeString(KEY_B64)
	block, _ := des.NewTripleDESCipher(key)
	pad := 8 - len(plain)%8
	padded := make([]byte, len(plain)+pad)
	copy(padded, plain)
	for i := len(plain); i < len(padded); i++ {
		padded[i] = byte(pad)
	}
	ct := make([]byte, len(padded))
	for i := 0; i < len(padded); i += 8 {
		block.Encrypt(ct[i:i+8], padded[i:i+8])
	}
	return ct
}

func toHexBase64(ct []byte) string {
	return hex.EncodeToString([]byte(base64.StdEncoding.EncodeToString(ct)))
}
