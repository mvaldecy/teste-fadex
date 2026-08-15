FROM node:22-alpine AS deps
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /app

ARG NEXT_PUBLIC_APP_NAME="Fadex Helpdesk"
ARG NEXT_PUBLIC_API_BASE_URL="http://localhost:8080/api/v1"
ARG NEXT_PUBLIC_MAILPIT_URL=""
ARG NEXT_PUBLIC_DEMO_LOGIN=""
# Todas precisam existir NESTE estagio: o Next substitui NEXT_PUBLIC_* no
# bundle durante o 'npm run build'. Declarar so no estagio de execucao nao tem
# efeito nenhum sobre o que o navegador recebe.
ENV NEXT_PUBLIC_APP_NAME=${NEXT_PUBLIC_APP_NAME}
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
ENV NEXT_PUBLIC_MAILPIT_URL=${NEXT_PUBLIC_MAILPIT_URL}
ENV NEXT_PUBLIC_DEMO_LOGIN=${NEXT_PUBLIC_DEMO_LOGIN}

COPY --from=deps /app/node_modules node_modules
COPY . .
RUN npm run build

FROM node:22-alpine AS runner
WORKDIR /app

ARG NEXT_PUBLIC_APP_NAME="Fadex Helpdesk"
ARG NEXT_PUBLIC_API_BASE_URL="http://localhost:8080/api/v1"
ARG NEXT_PUBLIC_MAILPIT_URL=""
ARG NEXT_PUBLIC_DEMO_LOGIN=""
ENV NODE_ENV=production
ENV NEXT_PUBLIC_APP_NAME=${NEXT_PUBLIC_APP_NAME}
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
ENV NEXT_PUBLIC_MAILPIT_URL=${NEXT_PUBLIC_MAILPIT_URL}
ENV NEXT_PUBLIC_DEMO_LOGIN=${NEXT_PUBLIC_DEMO_LOGIN}

COPY --from=build /app/package.json package.json
COPY --from=build /app/package-lock.json package-lock.json
COPY --from=build /app/.next .next
COPY --from=build /app/node_modules node_modules

# Valor informativo. A porta real vem de PORT em tempo de execucao: o compose
# define PORT igual a porta publicada para o log do Next nao discordar da URL.
EXPOSE 3000
CMD ["npm", "run", "start"]
