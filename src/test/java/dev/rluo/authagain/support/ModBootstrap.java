package dev.rluo.authagain.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.mockito.MockedStatic;

import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Forces {@link dev.rluo.authagain.AuthAgainMod} to initialize outside a running
 * mod loader. Its static {@code MOD_INFO} field calls into {@link ModLoadingContext},
 * which NPEs when no mod is loading, so the first load has to happen under a stub.
 */
public final class ModBootstrap {

	private static boolean done;

	private ModBootstrap() {
	}

	public static synchronized void init() {
		if (done) {
			return;
		}
		try (MockedStatic<ModLoadingContext> ctx = mockStatic(ModLoadingContext.class)) {
			ModLoadingContext context = mock(ModLoadingContext.class);
			ModContainer container = mock(ModContainer.class);
			ctx.when(ModLoadingContext::get).thenReturn(context);
			when(context.getActiveContainer()).thenReturn(container);
			when(container.getModInfo()).thenReturn(null);
			Class.forName("dev.rluo.authagain.AuthAgainMod", true, ModBootstrap.class.getClassLoader());
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
		done = true;
	}
}
