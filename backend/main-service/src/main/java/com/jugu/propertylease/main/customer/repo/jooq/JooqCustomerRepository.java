package com.jugu.propertylease.main.customer.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.EMPLOYEE;
import static com.jugu.propertylease.main.jooq.Tables.ENTERPRISE;

import com.jugu.propertylease.main.customer.api.model.CustomerEmployeeInfo;
import com.jugu.propertylease.main.customer.api.model.CustomerEnterpriseInfo;
import com.jugu.propertylease.main.customer.repo.CustomerRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JooqCustomerRepository implements CustomerRepository {

    private final DSLContext dsl;

    public JooqCustomerRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ── Enterprise ──────────────────────────────────────────────────────

    @Override
    public Long insertEnterprise(String name, String contactName, String contactMobile,
                                 String remark, Long createdBy, OffsetDateTime now) {
        return dsl.insertInto(ENTERPRISE)
                .set(ENTERPRISE.NAME, name)
                .set(ENTERPRISE.STATUS, "ACTIVE")
                .set(ENTERPRISE.CONTACT_NAME, contactName)
                .set(ENTERPRISE.CONTACT_MOBILE, contactMobile)
                .set(ENTERPRISE.REMARK, remark)
                .set(ENTERPRISE.CREATED_BY, createdBy)
                .set(ENTERPRISE.CREATED_AT, now)
                .set(ENTERPRISE.UPDATED_AT, now)
                .returning(ENTERPRISE.ID)
                .fetchOne(ENTERPRISE.ID);
    }

    @Override
    public Optional<CustomerEnterpriseInfo> findEnterpriseById(Long id) {
        return dsl.select(ENTERPRISE.ID, ENTERPRISE.NAME, ENTERPRISE.STATUS)
                .from(ENTERPRISE)
                .where(ENTERPRISE.ID.eq(id))
                .fetchOptional(r -> new CustomerEnterpriseInfo(
                        r.get(ENTERPRISE.ID),
                        r.get(ENTERPRISE.NAME),
                        r.get(ENTERPRISE.STATUS)
                ));
    }

    @Override
    public List<CustomerEnterpriseInfo> findEnterprisesByStatus(String status, int offset, int limit) {
        var cond = status != null
                ? ENTERPRISE.STATUS.eq(status)
                : org.jooq.impl.DSL.trueCondition();
        return dsl.select(ENTERPRISE.ID, ENTERPRISE.NAME, ENTERPRISE.STATUS)
                .from(ENTERPRISE)
                .where(cond)
                .orderBy(ENTERPRISE.CREATED_AT.desc())
                .limit(limit).offset(offset)
                .fetch(r -> new CustomerEnterpriseInfo(
                        r.get(ENTERPRISE.ID),
                        r.get(ENTERPRISE.NAME),
                        r.get(ENTERPRISE.STATUS)
                ));
    }

    @Override
    public int countEnterprisesByStatus(String status) {
        var cond = status != null
                ? ENTERPRISE.STATUS.eq(status)
                : org.jooq.impl.DSL.trueCondition();
        return dsl.fetchCount(ENTERPRISE, cond);
    }

    @Override
    public void updateEnterprise(Long id, String name, String contactName,
                                 String contactMobile, String remark, OffsetDateTime now) {
        dsl.update(ENTERPRISE)
                .set(ENTERPRISE.NAME, name)
                .set(ENTERPRISE.CONTACT_NAME, contactName)
                .set(ENTERPRISE.CONTACT_MOBILE, contactMobile)
                .set(ENTERPRISE.REMARK, remark)
                .set(ENTERPRISE.UPDATED_AT, now)
                .where(ENTERPRISE.ID.eq(id))
                .execute();
    }

    @Override
    public void updateEnterpriseStatus(Long id, String status, OffsetDateTime now) {
        dsl.update(ENTERPRISE)
                .set(ENTERPRISE.STATUS, status)
                .set(ENTERPRISE.UPDATED_AT, now)
                .where(ENTERPRISE.ID.eq(id))
                .execute();
    }

    // ── Employee ─────────────────────────────────────────────────────────

    @Override
    public Long insertEmployee(Long enterpriseId, String name, String mobile,
                               Long createdBy, OffsetDateTime now) {
        return dsl.insertInto(EMPLOYEE)
                .set(EMPLOYEE.ENTERPRISE_ID, enterpriseId)
                .set(EMPLOYEE.NAME, name)
                .set(EMPLOYEE.MOBILE, mobile)
                .set(EMPLOYEE.STATUS, "ACTIVE")
                .set(EMPLOYEE.CREATED_BY, createdBy)
                .set(EMPLOYEE.CREATED_AT, now)
                .set(EMPLOYEE.UPDATED_AT, now)
                .returning(EMPLOYEE.ID)
                .fetchOne(EMPLOYEE.ID);
    }

    @Override
    public Optional<CustomerEmployeeInfo> findEmployeeById(Long id) {
        return dsl.select(EMPLOYEE.ID, EMPLOYEE.ENTERPRISE_ID,
                        EMPLOYEE.NAME, EMPLOYEE.MOBILE, EMPLOYEE.STATUS)
                .from(EMPLOYEE)
                .where(EMPLOYEE.ID.eq(id))
                .fetchOptional(r -> new CustomerEmployeeInfo(
                        r.get(EMPLOYEE.ID),
                        r.get(EMPLOYEE.ENTERPRISE_ID),
                        r.get(EMPLOYEE.NAME),
                        r.get(EMPLOYEE.MOBILE),
                        r.get(EMPLOYEE.STATUS)
                ));
    }

    @Override
    public List<CustomerEmployeeInfo> findEmployeesByEnterprise(Long enterpriseId, String status,
                                                                 int offset, int limit) {
        var cond = EMPLOYEE.ENTERPRISE_ID.eq(enterpriseId);
        if (status != null) cond = cond.and(EMPLOYEE.STATUS.eq(status));

        return dsl.select(EMPLOYEE.ID, EMPLOYEE.ENTERPRISE_ID,
                        EMPLOYEE.NAME, EMPLOYEE.MOBILE, EMPLOYEE.STATUS)
                .from(EMPLOYEE)
                .where(cond)
                .orderBy(EMPLOYEE.CREATED_AT.desc())
                .limit(limit).offset(offset)
                .fetch(r -> new CustomerEmployeeInfo(
                        r.get(EMPLOYEE.ID),
                        r.get(EMPLOYEE.ENTERPRISE_ID),
                        r.get(EMPLOYEE.NAME),
                        r.get(EMPLOYEE.MOBILE),
                        r.get(EMPLOYEE.STATUS)
                ));
    }

    @Override
    public int countEmployeesByEnterprise(Long enterpriseId, String status) {
        var cond = EMPLOYEE.ENTERPRISE_ID.eq(enterpriseId);
        if (status != null) cond = cond.and(EMPLOYEE.STATUS.eq(status));
        return dsl.fetchCount(EMPLOYEE, cond);
    }

    @Override
    public void updateEmployee(Long id, String name, String mobile, OffsetDateTime now) {
        dsl.update(EMPLOYEE)
                .set(EMPLOYEE.NAME, name)
                .set(EMPLOYEE.MOBILE, mobile)
                .set(EMPLOYEE.UPDATED_AT, now)
                .where(EMPLOYEE.ID.eq(id))
                .execute();
    }

    @Override
    public void updateEmployeeStatus(Long id, String status, OffsetDateTime now) {
        dsl.update(EMPLOYEE)
                .set(EMPLOYEE.STATUS, status)
                .set(EMPLOYEE.UPDATED_AT, now)
                .where(EMPLOYEE.ID.eq(id))
                .execute();
    }

    @Override
    public boolean existsActiveEmployeeInEnterprise(Long employeeId, Long enterpriseId) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(EMPLOYEE)
                        .where(EMPLOYEE.ID.eq(employeeId))
                        .and(EMPLOYEE.ENTERPRISE_ID.eq(enterpriseId))
                        .and(EMPLOYEE.STATUS.eq("ACTIVE"))
        );
    }
}
