FROM docker.elastic.co/integrations/crawler:0.4.2

USER root

COPY entrypoint.sh /opt/liferay/entrypoint.sh

RUN chmod +x /opt/liferay/entrypoint.sh

USER app

WORKDIR /home/app

ENTRYPOINT ["/opt/liferay/entrypoint.sh"]
