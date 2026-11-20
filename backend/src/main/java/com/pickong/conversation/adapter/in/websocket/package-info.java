/**
 * STOMP 입력 어댑터 (@MessageMapping dm.send · dm.read). Principal을 회원 id로 바꿔 in-port를 호출하고,
 * 오류는 @MessageExceptionHandler로 /user/queue/dm/errors에 보냅니다. 업무 규칙은 두지 않습니다.
 */
package com.pickong.conversation.adapter.in.websocket;
