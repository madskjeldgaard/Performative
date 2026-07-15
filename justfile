quark_file_name := "Performative.quark"

changelog:
  git cliff > CHANGELOG.md

update_version VERSION:
  @if [ -z "{{VERSION}}" ]; then \
    echo "Usage: just update_version VERSION"; \
    exit 1; \
  fi
  # Check if version starts with a "v" (it has to for git cliff)
  if [[ ! "{{VERSION}}" =~ ^v ]]; then \
    echo "Error: Version must start with 'v' (e.g., v1.2.3)"; \
    exit 1; \
  fi
  # Update quark version
  sed -i '' -e "s/version: \".*\",/version: \"{{VERSION}}\",/" {{quark_file_name}}
  # New version tag
  git tag -a "{{VERSION}}" -m "Release version {{VERSION}}"
  # Generate new changelog
  just changelog
  git add {{quark_file_name}} CHANGELOG.md
  # Commit
  git commit -m "Update version to {{VERSION}}"
