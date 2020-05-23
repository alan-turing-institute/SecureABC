#!/usr/bin/env python3
# Tool to demonstrate the feasibility of encoding a SecureABC
# "AntiBody Certificate" into a standard QR-code.
# C Hicks 16-05-2020
#
# Dependencies:
#   zbar            http://zbar.sourceforge.net
#   opencv-python3  https://pypi.org/project/opencv-python/
#   matplotlib      https://pypi.org/project/matplotlib/
#   OpenSSL         https://pypi.org/project/pyOpenSSL/ (deprecated for crypto)

from pyzbar.pyzbar import decode
import matplotlib.pyplot as plt
import OpenSSL
from OpenSSL import crypto
import cv2
import base64
import io


# QR data class is used to load the QR data payload, extract the certificate
# details and verify the cryptographic signature.
class QR_data():
    def __init__(self, b64_data):
        readPos = 0
        self.raw_data = base64.b64decode(b64_data)
        user_img_length = int.from_bytes(self.raw_data[:2], 'big')
        readPos += 2
        self.user_img_bytes = self.raw_data[readPos:readPos+user_img_length]
        readPos += user_img_length
        user_name_length = int.from_bytes(self.raw_data[readPos:readPos+2], 'big')
        readPos += 2
        self.user_name_bytes = self.raw_data[readPos:readPos+user_name_length]
        readPos += user_name_length
        user_date_length = int.from_bytes(self.raw_data[readPos:readPos+2], 'big')
        readPos += 2
        self.date_bytes = self.raw_data[readPos:readPos+user_date_length]
        readPos += user_date_length
        user_CID_length = int.from_bytes(self.raw_data[readPos:readPos+2], 'big')
        readPos += 2
        self.user_CID_bytes = self.raw_data[readPos:readPos+user_CID_length]
        readPos += user_CID_length
        self.raw_verify_bytes = self.raw_data[0:readPos]
        cert_sign_length = int.from_bytes(self.raw_data[readPos:readPos+2], 'big')
        readPos += 2
        self.cert_sign_bytes = self.raw_data[readPos:readPos+cert_sign_length]

    # Return the user photograph bytes
    def get_user_photo(self):
        return self.user_img_bytes

    # Return the certificate asignee name as a string
    def get_user_name(self):
        return self.user_name_bytes.decode('utf-8')

    # Return the certificate valid date as a string
    def get_user_date(self):
        return self.date_bytes.decode('utf-8')

    # Return the CID number as a string
    def get_user_CID(self):
        return self.user_CID_bytes.decode('utf-8')

    # Return the cryptographic signature on the unsigned certificate bytes
    def get_signature(self):
        return self.cert_sign_bytes

    # Verify the signature on the certificate
    def verify_signature(self, cert):
        cert_verify = OpenSSL.crypto.verify(cert, self.cert_sign_bytes,
                                                self.raw_verify_bytes, 'sha256')
        if cert_verify is None:
            return True
        else:
            return False

#
def main():
    signer_cert_filename = 'OpenSSLKeys/sign_cert.pem'
    qr_code_filename = 'user_qr.png'

    # Load signer certificate public key for verifying user certificate data
    verify_cert_file = open(signer_cert_filename, "r")
    verify_cert = verify_cert_file.read()
    verify_cert_file.close()
    if verify_cert.startswith('-----BEGIN '):
        pubkey = crypto.load_certificate(crypto.FILETYPE_PEM, verify_cert)
    else:
        pubkey = crypto.load_pkcs12(verify_cert).get_certificate()

    # Read QR code
    print('Scanning {} for QR code credential'.format(qr_code_filename))
    qr_data_base64 = decode(cv2.imread(qr_code_filename))[0].data

    # Read certificate data from QR code
    qr = QR_data(qr_data_base64)
    print('QR certificate details: ')
    print('\tName: {}'.format(qr.get_user_name()))
    print('\tDate: {}'.format(qr.get_user_date()))
    print('\t CID: {}'.format(qr.get_user_CID()))
    print('\tSign: {}'.format(base64.encodebytes(qr.get_signature())))

    # Verify signature on certificate data
    print('\t Valid signature: {}'.format(qr.verify_signature(pubkey)))

    # Read and display photo from QR code
    user_photo_stream = io.BytesIO(qr.get_user_photo())
    user_photo = plt.imread(user_photo_stream, 'jpeg')
    plt.imshow(user_photo, cmap='gray')
    plt.axis('off')
    plt.show()

if __name__ == '__main__':
    main()
