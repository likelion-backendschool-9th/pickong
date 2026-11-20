/**
 * payment 모듈이 호출해 들어오는 입구. payment의 PaymentPurposeHandler(SPI)를 구현하고,
 * 변환만 한 뒤 PurchaseGrantUseCase를 호출합니다. 규칙은 application/service에 있습니다.
 */
package com.pickong.collection.adapter.in.payment;
