#!/usr/bin/env bash

echo "Generate Certs to $CERT_PATH"

createCert() { local certName="$1"
    if [ ! -f $CERT_PATH/$certName.key ]; then
      openssl genrsa -out $CERT_PATH/$certName.key 2048
      echo generate $CERT_PATH/$certName.key success
    else
      echo exist and skip $CERT_PATH/$certName.key.
    fi

    if [ ! -f $CERT_PATH/$certName.csr ]; then
      openssl req -new -key $CERT_PATH/$certName.key -out $CERT_PATH/$certName.csr -subj "/CN=Cert-$certName ($(date))"
      echo generate $CERT_PATH/$certName.csr success
    else
      echo exist and skip $CERT_PATH/$certName.csr.
    fi

    if [ ! -f $CERT_PATH/$certName.crt ]; then
      openssl x509 -req -days 3650 -CA $CERT_PATH/ca.crt -CAkey $CERT_PATH/ca.key -CAcreateserial -in $CERT_PATH/$certName.csr -out $CERT_PATH/$certName.crt
      echo generate $CERT_PATH/$certName.crt success
    else
      echo exist and skip $CERT_PATH/$certName.crt.
    fi

    if [ ! -f $CERT_PATH/${certName}_pkcs8.key ]; then
      openssl pkcs8 -topk8 -in $CERT_PATH/$certName.key -out $CERT_PATH/${certName}_pkcs8.key -nocrypt
      chmod +r $CERT_PATH/${certName}_pkcs8.key
      echo generate $CERT_PATH/${certName}_pkcs8.key success
    else
      echo exist and skip $CERT_PATH/${certName}_pkcs8.key.
    fi
}

if [ ! -f $CERT_PATH/ca.key ]; then
  openssl genrsa -out $CERT_PATH/ca.key 2048
  echo generate $CERT_PATH/ca.key success
else
  echo exist and skip $CERT_PATH/ca.key.
fi

if [ ! -f $CERT_PATH/ca.csr ]; then
  openssl req -new -key $CERT_PATH/ca.key -out $CERT_PATH/ca.csr -subj "/CN=Net Tunnel Root CA ($(date))"
  echo generate $CERT_PATH/ca.csr success
else
  echo exist and skip $CERT_PATH/ca.csr.
fi

if [ ! -f $CERT_PATH/ca.crt ]; then
  openssl x509 -req -days 3650 -in $CERT_PATH/ca.csr -signkey $CERT_PATH/ca.key -out $CERT_PATH/ca.crt
  echo generate $CERT_PATH/ca.crt success
else
  echo exist and skip $CERT_PATH/ca.crt.
fi

createCert "server"
createCert "client"
CERT_CLIENT_PATH=$CERT_PATH/client_config
mkdir -p $CERT_CLIENT_PATH
cp $CERT_PATH/ca.crt $CERT_CLIENT_PATH/
cp $CERT_PATH/client.crt $CERT_CLIENT_PATH/
cp $CERT_PATH/client_pkcs8.key $CERT_CLIENT_PATH/


