#!/bin/bash

# Add the container ID to the end of the UnifiedMetrics configuration
echo "server:" >> /server/extensions/UnifiedMetrics/config.yml
echo "  name: $PUFFIN_CONTAINER_ID" >> /server/extensions/UnifiedMetrics/config.yml

# Start the Minestom server
java -jar /server/server.jar