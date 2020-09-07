# SecureABC Paper and Reference Implementation

## Paper
The latest SecureABC AntiBody Certificates paper can be found at [SecureABC.pdf](SecureABC.pdf). The authors welcome feedback and may be contacted by email using the details in the paper or by clicking the following [link](mailto:chicks@turing.ac.uk;dbutler@turing.ac.uk;cm@warwick.ac.uk;jon.crowcroft@cl.cam.ac.uk?subject=[SecureABC]).

Changes to the paper are documented in the [changelog](changelog.md).

## Reference Implementation

This repository also includes a simple reference implementation (in Python3), and proof-of-concept, of the SecureABC AntiBody Certificate proposal. In particular we currently demonstrate the feasibility of our user certificates with a generation and verification script written in Python and a verification app written for Android.

To generate an example SecureABC QR code, run [Generate_QR_Credential.py](Generate_QR_Credential.py), and to verify run either [Verify_QR_Credential.py](Verify_QR_Credential.py) or the Android application which is located in the [SecureABCVerifier](SecureABCVerifier/) directory.

. Credentials, which can be displayed as standard  QR codes, are signed using ECDSA over brainpoolP512r1 and comprise a user photo, name, a unique Certificate ID (CID) number and a validity period. Credentials are completely self-contained and can be verified offline, without interacting with the signer. Revocation is based on distributing revoked CIDs to verifiers.

Example credential verification using our demonstration application:


<img style="margin-left: auto; margin-right: auto;" src="user_qr.png" alt="Demo SecureABC QR Cerification Application", width=70%>


## Running the code

The requirements.txt file contains all dependencies.

To run the implmentation you can use the following commands

```
virtualenv venv
source venv/bin/activate
pip install -r requirements.txt
python3 Generate_QR_Credential.py 
python3 Verify_QR_Credential.py
```


