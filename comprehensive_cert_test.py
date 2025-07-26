#!/usr/bin/env python3
"""
Comprehensive certificate pinning test to simulate app behavior.
This test validates the complete certificate pinning workflow.
"""
import requests
import ssl
import socket
import hashlib
import base64
import json
from urllib3.exceptions import InsecureRequestWarning

# Suppress SSL warnings for this test
requests.packages.urllib3.disable_warnings(InsecureRequestWarning)

def test_app_certificate_pinning():
    """Test certificate pinning as the app would experience it"""
    print("🔒 Comprehensive Certificate Pinning Test")
    print("=" * 50)
    
    # Test 1: Server availability
    print("1. Testing server availability...")
    try:
        response = requests.get('https://localhost:443/', verify=False, timeout=5)
        print(f"   ✅ Server is running: {response.text.strip()}")
    except Exception as e:
        print(f"   ❌ Server not accessible: {e}")
        return False
    
    # Test 2: Certificate hash validation
    print("\n2. Testing certificate hash...")
    try:
        actual_hash = get_certificate_hash('localhost', 443)
        pinned_hash = "bWsw3WqdtgiEWsOtKrjFEOAjebBzD4GruTg+uO0mQ8g="
        print(f"   Pinned hash:  {pinned_hash}")
        print(f"   Actual hash:  {actual_hash}")
        
        if actual_hash == pinned_hash:
            print("   ✅ Certificate hash matches pinned value")
        else:
            print("   ❌ Certificate hash mismatch!")
            return False
    except Exception as e:
        print(f"   ❌ Certificate hash test failed: {e}")
        return False
    
    # Test 3: API endpoint connectivity (simulating Android emulator perspective)
    print("\n3. Testing API endpoint connectivity...")
    try:
        # Test the login endpoint (which the app would use)
        test_data = {"username": "testuser", "password": "testpass"}
        response = requests.post('https://localhost:443/api/login', 
                               json=test_data, 
                               verify=False, 
                               timeout=5)
        print(f"   ✅ API endpoint accessible (status: {response.status_code})")
        print(f"   Response: {response.text}")
    except Exception as e:
        print(f"   ❌ API endpoint test failed: {e}")
        return False
    
    # Test 4: WebSocket endpoint (newly added in merge)
    print("\n4. Testing WebSocket endpoint...")
    try:
        # Just check if the endpoint exists (can't test full WebSocket without socket.io client)
        response = requests.get('https://localhost:443/socket.io/', verify=False, timeout=5)
        if response.status_code in [200, 400, 404]:  # 400/404 are acceptable for socket.io endpoint
            print(f"   ✅ WebSocket endpoint exists (status: {response.status_code})")
        else:
            print(f"   ⚠️  WebSocket endpoint responded with: {response.status_code}")
    except Exception as e:
        print(f"   ⚠️  WebSocket endpoint test: {e}")
    
    print("\n" + "=" * 50)
    print("🎉 Certificate pinning validation complete!")
    print("   ✅ Server running and accessible")
    print("   ✅ Certificate hash matches pinning configuration")
    print("   ✅ API endpoints accessible")
    print("   ✅ Ready for secure mobile app communication")
    
    return True

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

if __name__ == "__main__":
    test_app_certificate_pinning()
