// =============================================================================
// 文件：package-structure.md（伪代码说明，不是实际 Java 文件）
// 目的：描述各模块包结构，开发时按此建包
// =============================================================================

/*
main-service/src/main/java/com/jugu/propertylease/main/

├── account/
│   ├── api/                          ← 对外 Port（Interface，其他模块依赖此包）
│   │   ├── AccountCommandPort.java
│   │   └── AccountQueryPort.java
│   ├── delegate/                     ← OpenAPI Delegate 薄转接层
│   │   ├── AccountAccountsApiDelegateImpl.java
│   │   ├── AccountTransactionsApiDelegateImpl.java
│   │   ├── AccountTopUpApiDelegateImpl.java
│   │   └── InternalAccountApiDelegateImpl.java
│   ├── repo/
│   │   ├── AccountRepository.java
│   │   ├── AccountTransactionRepository.java
│   │   └── jooq/
│   │       ├── JooqAccountRepository.java
│   │       └── JooqAccountTransactionRepository.java
│   └── service/
│       ├── AccountLifecycleService.java   ← 开户/销户/冻结/解冻
│       ├── AccountDeductService.java      ← 扣款（含平摊逻辑）
│       ├── AccountTopUpService.java       ← 充值发起 + 回调处理
│       └── AccountQueryService.java
│
├── meter/
│   ├── api/
│   │   ├── MeterCommandPort.java
│   │   ├── MeterQueryPort.java
│   │   └── MeterScheduleTrigger.java
│   ├── delegate/
│   │   ├── MeterReadingsApiDelegateImpl.java
│   │   ├── MeterSettlementsApiDelegateImpl.java
│   │   ├── MeterPricesApiDelegateImpl.java
│   │   └── InternalMeterApiDelegateImpl.java
│   ├── repo/
│   │   ├── MeterReadingRepository.java
│   │   ├── MeterDailySettlementRepository.java
│   │   ├── MeterPriceConfigRepository.java
│   │   └── jooq/
│   │       ├── JooqMeterReadingRepository.java
│   │       ├── JooqMeterDailySettlementRepository.java
│   │       └── JooqMeterPriceConfigRepository.java
│   └── service/
│       ├── MeterReadingService.java        ← 录入 + IoT 推送（幂等）
│       ├── MeterDailySettlementService.java ← 日结核心逻辑
│       └── MeterPriceService.java
│
├── contract/
│   ├── api/
│   │   ├── ContractQueryPort.java
│   │   └── ContractScheduleTrigger.java
│   ├── delegate/
│   │   ├── ContractContractsApiDelegateImpl.java
│   │   ├── ContractRoomsApiDelegateImpl.java
│   │   ├── ContractTenanciesApiDelegateImpl.java
│   │   └── InternalContractApiDelegateImpl.java
│   ├── repo/
│   │   ├── ContractRepository.java
│   │   ├── ContractRoomRepository.java
│   │   ├── TenancyRepository.java
│   │   ├── ContractRentBillRepository.java
│   │   ├── ContractOperationLogRepository.java
│   │   └── jooq/
│   │       └── (Jooq impls)
│   └── service/
│       ├── ContractLifecycleService.java   ← 创建/签约/激活/取消/发起退房
│       ├── ContractRoomService.java        ← 分配/移除房间
│       ├── CheckInService.java             ← 入住编排（@Transactional，跨模块调用）
│       ├── CheckoutService.java            ← 退宿编排（发起结算）
│       ├── ContractCallbackService.java    ← 处理各类内部回调
│       ├── RentBillService.java            ← 租金账单生成（schedule 触发）
│       └── ContractQueryService.java
│
├── settlement/
│   ├── api/
│   │   └── SettlementCommandPort.java
│   ├── delegate/
│   │   ├── SettlementSettlementsApiDelegateImpl.java
│   │   └── InternalSettlementApiDelegateImpl.java
│   ├── repo/
│   │   ├── SettlementRepository.java
│   │   ├── SettlementItemRepository.java
│   │   └── jooq/
│   │       └── (Jooq impls)
│   └── service/
│       ├── TenantCheckoutSettlementService.java   ← 个人退宿结算
│       └── ContractTerminationSettlementService.java ← 合同退房结算
│
└── schedule/
    ├── delegate/
    │   └── ScheduleTasksApiDelegateImpl.java
    ├── repo/
    │   ├── ScheduleTaskLogRepository.java
    │   └── jooq/
    │       └── JooqScheduleTaskLogRepository.java
    └── service/
        ├── ScheduleTaskRunner.java     ← @Scheduled Cron 入口
        └── ScheduleTaskLogService.java ← 日志记录 + 防重检查
*/
