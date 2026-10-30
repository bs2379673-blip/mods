#!/usr/bin/env python3
"""Cliente RCON minimo: rcon.py <puerto> <clave> <comando> [<comando> ...]"""
import socket
import struct
import sys
import time


def packet(pid, ptype, body):
    data = struct.pack('<ii', pid, ptype) + body.encode() + b'\x00\x00'
    return struct.pack('<i', len(data)) + data


def read(sock):
    raw = b''
    while len(raw) < 4:
        raw += sock.recv(4 - len(raw))
    length = struct.unpack('<i', raw)[0]
    data = b''
    while len(data) < length:
        data += sock.recv(length - len(data))
    return data[8:-2].decode(errors='replace')


port, password, commands = int(sys.argv[1]), sys.argv[2], sys.argv[3:]
sock = socket.create_connection(('127.0.0.1', port), timeout=30)
sock.sendall(packet(1, 3, password))
read(sock)
for command in commands:
    if command.startswith('sleep '):
        time.sleep(float(command.split()[1]))
        continue
    sock.sendall(packet(2, 2, command))
    print('> ' + command)
    print(read(sock))
