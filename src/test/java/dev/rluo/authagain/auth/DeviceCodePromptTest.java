package dev.rluo.authagain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;

class DeviceCodePromptTest {

	@Test
	void fromCopiesEveryFieldOffTheDeviceCode() {
		MsaDeviceCode code = mock(MsaDeviceCode.class);
		when(code.getUserCode()).thenReturn("ABCD-EFGH");
		when(code.getVerificationUri()).thenReturn("https://microsoft.com/link");
		when(code.getDirectVerificationUri()).thenReturn("https://microsoft.com/link?code=ABCD-EFGH");
		when(code.getExpireTimeMs()).thenReturn(1234L);

		DeviceCodePrompt prompt = DeviceCodePrompt.from(code);

		assertThat(prompt.userCode()).isEqualTo("ABCD-EFGH");
		assertThat(prompt.verificationUri()).isEqualTo("https://microsoft.com/link");
		assertThat(prompt.directVerificationUri()).isEqualTo("https://microsoft.com/link?code=ABCD-EFGH");
		assertThat(prompt.expiresAtMs()).isEqualTo(1234L);
	}
}
