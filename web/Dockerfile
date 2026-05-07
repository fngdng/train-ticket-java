FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Copy clone content into the nginx root so the clone is served at /
COPY vetau-clone /usr/share/nginx/html/
EXPOSE 80
