FROM us-docker.pkg.dev/gitlab-runner-189405/gcr.io/docker-sbt

# Set the working directory
WORKDIR /workspace

# Copy the project files
COPY . /workspace

# Set the entrypoint
ENTRYPOINT ["sbt"]
