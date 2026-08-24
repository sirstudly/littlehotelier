FROM debian:bookworm-slim AS build

ARG THREEPROXY_VERSION=0.9.5

RUN apt-get update \
    && apt-get install -y --no-install-recommends gcc make libc6-dev ca-certificates wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
RUN wget -O 3proxy.tar.gz \
        "https://github.com/3proxy/3proxy/archive/refs/tags/${THREEPROXY_VERSION}.tar.gz" \
    && tar xf 3proxy.tar.gz \
    && cd "3proxy-${THREEPROXY_VERSION}" \
    && make -f Makefile.Linux \
    && install -D -m 0755 bin/3proxy /out/usr/bin/3proxy

FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends gettext-base ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /etc/3proxy

COPY --from=build /out/usr/bin/3proxy /usr/bin/3proxy
COPY 3proxy.cfg.template /etc/3proxy/3proxy.cfg.template
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 3128
ENTRYPOINT ["/docker-entrypoint.sh"]
