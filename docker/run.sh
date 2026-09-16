#!/usr/bin/env bash
set -e

echo "==> Starting JTrac NG container on http://localhost:8888"
docker run -d \
  -p 8888:8080 \
  -v jtrac_data:/jtrac-data \
  --name jtrac-ng \
  jtrac-ng:latest
echo "==> JTrac NG is running! Access it at: http://localhost:8888"
