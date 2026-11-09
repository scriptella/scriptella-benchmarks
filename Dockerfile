FROM maven:3.9.11-eclipse-temurin-17
ARG SCRIPTELLA_VERSION=1.7.0
ARG BENCHMARK_REVISION=n/a
RUN apt-get update && apt-get install -y --no-install-recommends python3 python3-venv && rm -rf /var/lib/apt/lists/*
RUN python3 -m venv /opt/pandas
COPY requirements.txt /app/requirements.txt
RUN /opt/pandas/bin/pip install --no-cache-dir -r /app/requirements.txt
ENV PATH="/opt/pandas/bin:${PATH}"
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B -ntp -Dstyle.color=never -Dscriptella.version=${SCRIPTELLA_VERSION} dependency:go-offline
COPY src src
COPY tools tools
RUN mvn -q -B -ntp -Dstyle.color=never -Dscriptella.version=${SCRIPTELLA_VERSION} package
ENV SCRIPTELLA_VERSION=${SCRIPTELLA_VERSION} BENCHMARK_REVISION=${BENCHMARK_REVISION}
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-cp", "/app/target/classes:/app/target/dependency/*", "org.scriptella.benchmarks.Benchmark"]
CMD ["run"]
