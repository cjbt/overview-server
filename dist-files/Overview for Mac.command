#!/bin/bash

DIR="$(dirname "$0")"
(cd "$DIR" && runner/runner -Doverview.multi_user=false "$@")
