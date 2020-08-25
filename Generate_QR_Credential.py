#!/usr/bin/env python3
# Tool to demonstrate the feasibility of encoding a SecureABC
# "AntiBody Certificate" into a standard QR-code.
# C Hicks 16-05-2020
#
# Dependencies:
#   qrcode 6.1  https://pypi.org/project/qrcode/
#   matplotlib  https://pypi.org/project/matplotlib/
#   OpenSSL     https://pypi.org/project/pyOpenSSL/ (deprecated for crypto)

import qrcode
import matplotlib.pyplot as plt
import base64
import OpenSSL
from OpenSSL import crypto
import time

# Certificate data object
class CertificateData:

    # Initialise certificate data
    def __init__(self, user_img, user_name, user_date, user_CID):
        self.user_img = user_img  # user_img_arr = np.asarray(user_img)
        self.user_name = user_name
        self.user_date = user_date
        self.user_CID = user_CID
        self.cert_bytes = self.getByteArray()

    # Return the certificate data as a byte array with variable field lengths
    def getByteArray(self):
        user_img_length = len(self.user_img).to_bytes(2, 'big')
        user_img_bytes = self.user_img
        user_name_len = len(self.user_name).to_bytes(2, 'big')
        user_name_bytes = bytearray(self.user_name, 'utf-8')
        user_date_len = len(self.user_date).to_bytes(2, 'big')
        user_date_bytes = bytearray(self.user_date, 'utf-8')
        user_CID_len = len(self.user_CID).to_bytes(2, 'big')
        user_CID_bytes = bytearray(self.user_CID, 'utf-8')

        #
        cert_bytes = user_img_length + user_img_bytes +\
                        user_name_len + user_name_bytes +\
                            user_date_len + user_date_bytes +\
                                user_CID_len + user_CID_bytes

        return cert_bytes

    # Signs the certificate data using pkey and then returns the certificate
    # data, plus the signature, as a bytearray
    def getSignedCertificateByteArray(self, pkey):
        tic = time.perf_counter()
        cert_sign = OpenSSL.crypto.sign(pkey, self.cert_bytes, "sha256")
        toc = time.perf_counter()
        print('{} seconds to generate signature'.format((toc-tic)*1000))
        cert_sign_len = len(cert_sign).to_bytes(2, 'big')
        cert_sign_bytes = bytearray(cert_sign)

        return self.cert_bytes + cert_sign_len + cert_sign_bytes

def main():
    signer_pkey_filename = 'OpenSSLKeys/sign_key.pem'
    user_image_filename = 'user_bw.jpeg'
    qr_code_filename = 'user_qr.png'

    # Define QR code specification
    qr = qrcode.QRCode(
        version=40,
        error_correction=qrcode.constants.ERROR_CORRECT_L,
        box_size=100,
        border=4,
    )

    # Load private key for signing user data
    key_file = open(signer_pkey_filename, "r")
    key = key_file.read()
    key_file.close()
    if key.startswith('-----BEGIN '):
        pkey = crypto.load_privatekey(crypto.FILETYPE_PEM, key)
    else:
        pkey = crypto.load_pkcs12(key).get_privatekey()

    tic = time.perf_counter()

    # Create user data
    user_img = open(user_image_filename, "rb").read()
    user_name = "Alice Doe"
    user_date = "16/08/2020 - 16/11/2020"
    user_CID = "0x1fc60e1a4e238ac6cce9d79097a268af"
    user_certData = CertificateData(user_img, user_name, user_date, user_CID)

    # Get signed user certificate and build QR code
    signed_user_cert = user_certData.getSignedCertificateByteArray(pkey)

    # qr.add_data(base64.b64encode(signed_user_cert))
    qr.add_data(base64.b64encode(signed_user_cert))

    try:
        qr.make(fit=False)
    except qrcode.exceptions.DataOverflowError:
        print('User data too big for QR code. Please optimise image.')
        exit()

    qr_img = qr.make_image(fill_color="black", back_color="white")
    plt.imshow(qr_img, cmap='gray')
    plt.axis('off')
    plt.savefig(qr_code_filename, bbox_inches='tight')
    toc = time.perf_counter()

    print('{} seconds to generate complete QR code output'.format((toc-tic)*1000))
    print('User QR code output to {}'.format(qr_code_filename))
    print('Done.')


if __name__ == '__main__':
    main()
