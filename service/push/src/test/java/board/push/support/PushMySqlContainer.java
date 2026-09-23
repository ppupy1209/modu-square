package board.push.support;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;

/** 운영과 같은 마이그레이션 SQL로 push 스키마를 만든 MySQL. */
public final class PushMySqlContainer {
    private PushMySqlContainer() {
    }

    public static MySQLContainer<?> create() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                .withDatabaseName("push")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(Path.of("../../infra/mysql/migrations/09-push.sql")),
                        "/docker-entrypoint-initdb.d/09-push.sql"
                );
    }
}
