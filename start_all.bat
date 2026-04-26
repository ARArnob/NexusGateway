@echo off
start "Backend Node 8001" java nexus.core.BackendNode 8001
start "Backend Node 8002" java nexus.core.BackendNode 8002
start "Backend Node 8003" java nexus.core.BackendNode 8003
start "Nexus Gateway" java nexus.core.Gateway
echo All services started!
