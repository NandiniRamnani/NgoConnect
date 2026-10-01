# -*- coding: utf-8 -*-
"""
Checks the MAIL_USERNAME / MAIL_PASSWORD in .env against Google.

Logs in and immediately disconnects - no email is sent to anyone. Run this after
editing .env to find out whether the credentials work, instead of restarting the
whole application and trying the reset form to find out.

    Double-click check-mail.bat, or:  python check-mail.py
"""
import os
import smtplib
import ssl
import sys

ENV = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")


def read_env(path):
    values = {}
    if not os.path.exists(path):
        return values
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.strip()
            if "=" in line and not line.startswith("#"):
                key, val = line.split("=", 1)
                values[key.strip()] = val.strip()
    return values


def main():
    env = read_env(ENV)
    user = env.get("MAIL_USERNAME", "")
    password = env.get("MAIL_PASSWORD", "")

    print()
    print("=" * 62)
    print(" NGOConnect - mail credential check")
    print("=" * 62)
    print(f" Sending account : {user or '(not set)'}")
    print(f" Password length : {len(password)} characters")
    print()

    # Catch the two mistakes that account for almost every failure here, before
    # bothering Google with a request that cannot possibly succeed.
    if not user or user == "youraddress@gmail.com":
        print(" PROBLEM: MAIL_USERNAME is not set to a real address.")
        print("          Edit .env and put your sending Gmail address there.")
        return 1

    if " " in password:
        print(" PROBLEM: the password contains spaces.")
        print("          Google displays App Passwords as 'abcd efgh ijkl mnop'")
        print("          for readability, but the spaces are NOT part of it.")
        print("          Remove them so it reads 'abcdefghijklmnop'.")
        return 1

    if len(password) != 16:
        print(f" PROBLEM: an App Password is exactly 16 characters; this is {len(password)}.")
        print()
        print("          A 16-character App Password is NOT your Gmail password.")
        print("          It is generated separately at:")
        print("              https://myaccount.google.com/apppasswords")
        print("          (2-Step Verification must be switched on first.)")
        return 1

    print(" Format looks correct. Contacting Google...")
    print()
    try:
        server = smtplib.SMTP("smtp.gmail.com", 587, timeout=25)
        server.ehlo()
        server.starttls(context=ssl.create_default_context())
        server.ehlo()
        try:
            server.login(user, password)
            print(" RESULT: SUCCESS")
            print(" Google accepted the credentials.")
            print(" Restart the backend and password-reset emails will be delivered.")
            code = 0
        except smtplib.SMTPAuthenticationError as err:
            detail = err.smtp_error
            detail = detail.decode(errors="replace") if isinstance(detail, bytes) else str(detail)
            print(f" RESULT: REJECTED BY GOOGLE (code {err.smtp_code})")
            print(" " + detail.replace("\n", " ")[:250])
            print()
            print(" The length is right, so the characters themselves are wrong.")
            print(" Generate a fresh App Password and copy it again:")
            print("     https://myaccount.google.com/apppasswords")
            code = 1
        server.quit()
        return code
    except Exception as err:                      # network, DNS, firewall
        print(f" CONNECTION FAILED: {type(err).__name__}: {err}")
        print(" Could not reach smtp.gmail.com:587 - check the internet connection")
        print(" or whether a firewall is blocking outbound port 587.")
        return 1


if __name__ == "__main__":
    sys.exit(main())
