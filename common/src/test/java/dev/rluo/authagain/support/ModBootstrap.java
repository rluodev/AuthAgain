package dev.rluo.authagain.support;

/**
 * Forces {@link dev.rluo.authagain.AuthAgainMod} to initialize outside a running
 * mod loader.
 */
public final class ModBootstrap {

	private static boolean done;

	private ModBootstrap() {
	}

	public static synchronized void init() {
		if (done) {
			return;
		}
		try {
			Class.forName("dev.rluo.authagain.AuthAgainMod", true, ModBootstrap.class.getClassLoader());
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
		done = true;
	}
}
