FROM python:3.12-slim
WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
# Sem estado: não grava nada em disco
USER nobody
EXPOSE 8090
HEALTHCHECK CMD python -c "import urllib.request;urllib.request.urlopen('http://localhost:8090/saude')" || exit 1
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090"]
