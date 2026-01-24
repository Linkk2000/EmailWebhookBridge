package work.chenhan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import work.chenhan.entity.ScaProcessRecord;

public interface ScaProcessRecordRepository
        extends JpaRepository<ScaProcessRecord, Long>, JpaSpecificationExecutor<ScaProcessRecord> {
}
