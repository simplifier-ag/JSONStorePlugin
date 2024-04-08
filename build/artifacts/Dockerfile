FROM simplifierag/simplifier-assembly:main_0.1.0 AS build

WORKDIR /build
COPY . /build/
RUN sbt assembly

FROM debian:bookworm-slim AS jsonstore_plugin_builder

RUN apt-get update && \
    apt-get upgrade -f -y
RUN mkdir -p /opt/plugin

#  provide plugin jar & config files
COPY --from=this assets /opt/plugin/assets
COPY --from=this setup.sh /opt/plugin/setup.sh
COPY --from=build /build/target/scala-2.12/jsonStore.jar /opt/plugin/assets

FROM scratch
COPY --from=jsonstore_plugin_builder /opt/plugin /opt/plugin

