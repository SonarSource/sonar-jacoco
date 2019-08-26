#!/usr/bin/env bash
set -euo pipefail

function configureTravis {
  mkdir -p ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v57 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}

configureTravis

regular_gradle_build_deploy_analyze
