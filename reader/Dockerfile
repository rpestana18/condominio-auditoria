FROM python:3.12-slim
WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
# Sem estado: não grava nada em disco
USER nobody
EXPOSE 8090
HEALTHCHECK CMD python -c "import urllib.request;urllib.request.urlopen('http://localhost:8090/health')" || exit 1
# Sem o access log do uvicorn: o leitor loga uma linha por requisição com o requestId (ADR 0008).
# JSON na nuvem: LOG_FORMATO=json
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090", "--no-access-log"]
