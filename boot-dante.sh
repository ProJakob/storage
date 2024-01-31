#!/bin/bash
apt update
apt install dante-server

danted -f dante-eth0.conf -p dante-eth0.pid -D
danted -f dante-eth1.conf -p dante-eth1.pid -D
danted -f dante-eth2.conf -p dante-eth2.pid -D
