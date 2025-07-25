#!/usr/bin/env python3
"""
Simple test to verify certificate pinning is working correctly.
This script validates that:
1. The backend server is running with HTTPS
2. The certificate hash matches what we expect
3. The certificate pinning configuration is correct
"""
import requests
import ssl
import socket
import hashlib
import base64
from urllib3.exceptions import InsecureRequestWarning

# Suppress SSL warnings for this test
requests.packages.urllib3.disable_warnings(InsecureRequestWarning)

def get_certificate_hash(hostname, port):
    """Get the SHA-256 hash of the server's certificate"""
    context = ssl.create_default_context()
    context.check_hostname = False
    context.verify_mode = ssl.CERT_NONE
    
    with socket.create_connection((hostname, port)) as sock:
        with context.wrap_socket(sock, server_hostname=hostname) as ssock:
            cert_der = ssock.getpeercert(binary_form=True)
            cert_hash = hashlib.sha256(cert_der).digest()
            return base64.b64encode(cert_hash).decode('utf-8')

def test_backend_connection():
    """Test that the backend is accessible"""
    try:
        response = requests.get('https://localhost:443/', verify=False, timeout=5)
        print(f"✅ Backend is running: {response.text.strip()}")
        return True
    except Exception as e:
        print(f"❌ Backend connection failed: {e}")
        return False

def test_certificate_hash():
    """Test that the certificate hash matches our expected value"""
    expected_hash = "bWsw3WqdtgiEWsOtKrjFEOAjebBzD4GruTg+uO0mQ8g="
    try:
        actual_hash = get_certificate_hash('localhost', 443)
        print(f"Expected hash: {expected_hash}")
        print(f"Actual hash:   {actual_hash}")
        
        if actual_hash == expected_hash:
            print("✅ Certificate hash matches!")
            return True
        else:
            print("❌ Certificate hash mismatch!")
            return False
    except Exception as e:
        print(f"❌ Certificate hash test failed: {e}")
        return False

def main():
    print("🔒 Certificate Pinning Test")
    print("=" * 40)
    
    # Test 1: Backend connection
    backend_ok = test_backend_connection()
    
    # Test 2: Certificate hash validation
    cert_ok = test_certificate_hash()
    
    print("\n" + "=" * 40)
    if backend_ok and cert_ok:
        print("🎉 All tests passed! Certificate pinning should work correctly.")
    else:
        print("⚠️  Some tests failed. Check configuration.")

if __name__ == "__main__":
    main()
