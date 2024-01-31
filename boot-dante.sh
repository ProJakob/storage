#!/bin/bash
apt update
apt install -y dante-server screen

rm -r configs >> /dev/null
rm -r pids >> /dev/null

mkdir configs
mkdir pids

for i in $(ls /sys/class/net | grep eth); do
    echo "Creating interface config: $i"
    curl -s https://raw.githubusercontent.com/ProJakob/storage/main/dante.conf | sed 's\REPLACE_INTERFACE\'"$i"'\g' | tee "configs/dante-$i.conf" >> /dev/null
    touch "pids/dante-$i.pid"
    screen -dmS "dante-$i" sh -c "danted -f 'configs/dante-$i.conf' -p 'pids/dante-$i.pid'; exec bash"
    sleep 0.5  # Add a short delay to ensure the screen session is created before checking its status
done
