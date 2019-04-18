#!/usr/bin/env python

from kafka import KafkaProducer
import json
import os
import re


host = os.environ.get("KAFKA_HOST", "localhost")
port = os.environ.get("KAFKA_PORT", "9092")
producer = KafkaProducer(bootstrap_servers=f"{host}:{port}")

def publish_to_kafka(filepath, topic):
    with open(filepath) as messages:
        for message in messages:
            if message.isspace():
                continue
            m = json.loads(message)
            e = m["event"]
            d = json.loads(m['data'])
            if e.endswith("Destroyed"):
                key = d['_id'].encode('utf8')
            else:
                key = d[list(d.keys())[0]]['id'].encode('utf8')
                print(list(d.keys())[0])
            msg = message.encode('utf8')
            producer.send(topic=topic, key=key, value=msg)

publish_to_kafka("./model.txt", "dev.model")
producer.flush()
