SUMMARY = "Production-Grade Container Scheduling and Management"
DESCRIPTION = "Lightweight Kubernetes, intended to be a fully compliant Kubernetes."
HOMEPAGE = "https://k3s.io/"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${S}/src/import/LICENSE;md5=2ee41112a44fe7014dce33e26468ba93"

SRC_URI = "git://github.com/rancher/k3s.git;branch=release-1.19;name=k3s \
           file://k3s.service \
           file://k3s-agent.service \
           file://k3s-agent \
           file://k3s-clean \
           file://cni-containerd-net.conf \
           file://0001-Finding-host-local-in-usr-libexec.patch;patchdir=src/import \
          "
SRC_URI[k3s.md5sum] = "363d3a08dc0b72ba6e6577964f6e94a5"
SRCREV_k3s = "b11612e2744f39f01bfd208ff97315930c483667"

PV = "v1.19.5+git${SRCPV}"

CNI_NETWORKING_FILES ?= "${WORKDIR}/cni-containerd-net.conf"

inherit go goarch systemd cni_networking

PACKAGECONFIG = ""
PACKAGECONFIG[upx] = ",,upx-native"
GO_IMPORT = "import"
GO_BUILD_LDFLAGS = "-X github.com/rancher/k3s/pkg/version.Version=${PV} \
                    -X github.com/rancher/k3s/pkg/version.GitCommit=${@d.getVar('SRCREV_k3s', d, 1)[:8]} \
                    -w -s \
                   "
BIN_PREFIX ?= "${exec_prefix}/local"

do_compile() {
        export GOPATH="${S}/src/import/.gopath:${S}/src/import/vendor:${STAGING_DIR_TARGET}/${prefix}/local/go"
        export CGO_ENABLED="1"
        export GOFLAGS="-mod=vendor"
        cd ${S}/src/import
        ${GO} build -tags providerless -ldflags "${GO_BUILD_LDFLAGS}" -o ./dist/artifacts/k3s ./cmd/server/main.go
        # Use UPX if it is enabled (and thus exists) to compress binary
        if command -v upx > /dev/null 2>&1; then
                upx -9 ./dist/artifacts/k3s
        fi
}
do_install() {
        install -d "${D}${BIN_PREFIX}/bin"
        install -m 755 "${S}/src/import/dist/artifacts/k3s" "${D}${BIN_PREFIX}/bin"
        ln -sr "${D}/${BIN_PREFIX}/bin/k3s" "${D}${BIN_PREFIX}/bin/crictl"
        ln -sr "${D}/${BIN_PREFIX}/bin/k3s" "${D}${BIN_PREFIX}/bin/kubectl"
        install -m 755 "${WORKDIR}/k3s-clean" "${D}${BIN_PREFIX}/bin"

        # Install systemd services
        install -d ${D}${systemd_system_unitdir}
        install -Dm0644 ${WORKDIR}/k3s.service ${D}${systemd_system_unitdir}
        install -Dm0644 ${WORKDIR}/k3s-agent.service ${D}${systemd_system_unitdir}
        sed -i -e 's,@BASE_BINDIR@,${base_bindir},g' \
        		-e 's,@BINDIR@,${bindir},g' \
        		-e 's,@SBINDIR@,${sbindir},g' \
        		${D}${systemd_system_unitdir}/k3s.service ${D}${systemd_system_unitdir}/k3s-agent.service
        install -m 755 "${WORKDIR}/k3s-agent" "${D}${BIN_PREFIX}/bin"
}

PACKAGES =+ "${PN}-server ${PN}-agent"

SYSTEMD_PACKAGES = "${PN} ${PN}-agent"
SYSTEMD_SERVICE_${PN} = "k3s.service"
SYSTEMD_SERVICE_${PN}-agent = "k3s-agent.service"
# SYSTEMD_AUTO_ENABLE_${PN}-agent = "enable"
# SYSTEMD_AUTO_ENABLE_${PN}-server = "enable"

FILES_${PN}-agent = "${BIN_PREFIX}/bin/k3s-agent"
FILES_${PN} += " \
    ${systemd_system_unitdir}/k3s.service \
    ${systemd_system_unitdir}/k3s-agent.service \
    ${BIN_PREFIX}/bin/* \
"

RDEPENDS_${PN} = "k3s-cni conntrack-tools coreutils findutils iptables iproute2 ipset virtual/containerd"
RDEPENDS_${PN}-server = "${PN}"
RDEPENDS_${PN}-agent = "${PN}"

RRECOMMENDS_${PN} = "kernel-module-xt-comment kernel-module-xt-mark kernel-module-xt-connmark kernel-module-vxlan"

RCONFLICTS_${PN} = "kubectl"

INHIBIT_PACKAGE_STRIP = "1"
INSANE_SKIP_${PN} += "ldflags already-stripped"
