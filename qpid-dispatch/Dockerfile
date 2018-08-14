FROM scholzj/qpid-dispatch:1.3.0

ENV QDROUTERD_CONFIG_FILE /var/lib/qdrouterd/etc/qdrouterd-custom.conf

COPY --chown=qdrouterd:qdrouterd  qdrouterd.conf /var/lib/qdrouterd/etc/qdrouterd-custom.conf
