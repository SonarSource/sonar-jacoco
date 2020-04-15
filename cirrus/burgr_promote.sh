#!/bin/bash

set -euo pipefail

# burgr notification can fail
{
    burgr-notify-promotion
} || {
    echo "burgr promotion notification failed"
}
