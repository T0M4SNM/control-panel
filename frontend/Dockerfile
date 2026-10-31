# Imagen del frontend: el build de Vite servido por un Caddy interno, que además reenvía /api al
# backend. Así el navegador ve frontend y API en el mismo origen. La construye GitHub Actions.

FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /src/dist /srv
EXPOSE 80
