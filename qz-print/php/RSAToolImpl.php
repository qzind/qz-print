<?php

class RSAToolImpl {

    public function loadPublicKey($file) {
        return openssl_get_publickey(file_get_contents($file));
    }

    public function loadPrivateKey($file) {
        return openssl_get_privatekey(file_get_contents($file));
    }

    public function encryptWithKey($input, $key) {
        return openssl_public_encrypt($input, $encrypted, $key) ? $encrypted : FALSE;
    }

    public function decryptWithKey($input, $key) {
        return openssl_private_decrypt($input, $decrypted, $key) ? $decrypted : FALSE;
    }

    public function signWithKey($input, $key) {
        return openssl_sign($input, $signature, $key) ? $signature : FALSE;
    }

    public function verifyWithKey($input, $signature, $key) {
        return openssl_verify($input, $signature, $key);
    }

}
