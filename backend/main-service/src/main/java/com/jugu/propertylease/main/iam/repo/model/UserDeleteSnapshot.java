package com.jugu.propertylease.main.iam.repo.model;

public record UserDeleteSnapshot(String userName, String mobile, String email, String sourceType,
                                 String userType) {
}
