# 第一阶段：构建前端静态资源
FROM node:20-alpine AS builder
WORKDIR /app

# 先复制 package 文件，利用 Docker 缓存加速依赖安装
COPY package.json package-lock.json* ./
RUN npm config set registry https://registry.npmmirror.com && npm ci

# 复制源码并构建
COPY . .
RUN npm run build

# 第二阶段：nginx 托管 dist
FROM nginx:1.27-alpine
WORKDIR /usr/share/nginx/html

# 清空默认页面，复制构建产物
RUN rm -rf /usr/share/nginx/html/*
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制自定义 nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
