#!/usr/bin/env bash
# Prepara un VPS recién creado con Ubuntu 24.04. Se ejecuta UNA vez, como root:
#   bash setup-server.sh
#
# - Actualiza el sistema y activa las actualizaciones de seguridad automáticas.
# - Crea 2 GB de swap (colchón por si algún proceso se dispara).
# - Cortafuegos: solo SSH (22), HTTP (80) y HTTPS (443).
# - fail2ban: bloquea IPs que prueban contraseñas por SSH.
# - Instala Docker desde el repositorio oficial de Docker.
# - Crea /opt/stacks, donde vive cada pila de Docker Compose (proxy, panel, apps...).
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
	echo "Ejecútalo como root (sudo bash setup-server.sh)" >&2
	exit 1
fi

echo "==> Actualizando el sistema"
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get -y upgrade
apt-get -y install ca-certificates curl gnupg ufw fail2ban unattended-upgrades
dpkg-reconfigure -f noninteractive unattended-upgrades

echo "==> Swap de 2 GB"
if ! swapon --show | grep -q /swapfile; then
	fallocate -l 2G /swapfile
	chmod 600 /swapfile
	mkswap /swapfile
	swapon /swapfile
	echo '/swapfile none swap sw 0 0' >> /etc/fstab
	# Usa la swap solo como último recurso
	echo 'vm.swappiness=10' > /etc/sysctl.d/99-swappiness.conf
	sysctl -p /etc/sysctl.d/99-swappiness.conf
fi

echo "==> Cortafuegos"
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

echo "==> fail2ban para SSH"
systemctl enable --now fail2ban

echo "==> Docker"
if ! command -v docker > /dev/null; then
	install -m 0755 -d /etc/apt/keyrings
	curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
	chmod a+r /etc/apt/keyrings/docker.asc
	echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
		> /etc/apt/sources.list.d/docker.list
	apt-get update
	apt-get -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker

mkdir -p /opt/stacks /var/backups/panel

echo
echo "Listo. Docker $(docker --version | cut -d' ' -f3 | tr -d ,) instalado."
echo "Sigue con el paso «Subir los archivos» de deploy/README.md."
