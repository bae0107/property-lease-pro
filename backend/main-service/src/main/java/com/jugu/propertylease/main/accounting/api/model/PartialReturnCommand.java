package com.jugu.propertylease.main.accounting.api.model;

import java.util.List;

/**
 * 部分退房结算命令。
 */
public record PartialReturnCommand(
        Long contractId,
        List<Long> returnedRoomIds
) {}
