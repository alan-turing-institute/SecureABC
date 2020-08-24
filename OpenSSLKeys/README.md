# To generate new keypair using OpenSSL

```
openssl ecparam -name secp521r1 -out secp521r1.pem
openssl ecparam -in secp521r1.pem -genkey -noout -out sign_key.pem
openssl req -config example-com.conf -new -x509 -sha256 -key sign_key.pem -days 365 -out sign_cert.pem
```
