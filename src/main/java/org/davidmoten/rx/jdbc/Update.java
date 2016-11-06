package org.davidmoten.rx.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Callable;

import io.reactivex.Emitter;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

public class Update {

	public static Single<Integer> create(Flowable<Connection> connections, List<Object> parameters, String sql) {
		return connections //
				.firstOrError() // 
				.flatMap(con -> Single.<Integer, PreparedStatement>using( //
						() -> Util.setParameters(con.prepareStatement(sql), parameters), //
						ps -> Single.<Integer>just(ps.executeUpdate()), //
						Util::closeAll));
	}

	public static <T> Flowable<T> create(Callable<Connection> connectionFactory, List<Object> parameters, String sql,
			Function<? super ResultSet, T> mapper) {
		Callable<PreparedStatement> resourceFactory = () -> {
			Connection con = connectionFactory.call();
			// TODO set parameters
			return con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
		};
		Function<PreparedStatement, Flowable<T>> singleFactory = ps -> create(ps, mapper);
		Consumer<PreparedStatement> disposer = ps -> Util.closeAll(ps);
		return Flowable.using(resourceFactory, singleFactory, disposer);
	}

	private static <T> Flowable<T> create(PreparedStatement ps, Function<? super ResultSet, T> mapper) {
		Callable<ResultSet> initialState = () -> {
			ps.execute();
			return ps.getGeneratedKeys();
		};
		BiConsumer<ResultSet, Emitter<T>> generator = (rs, emitter) -> {
			if (rs.next()) {
				emitter.onNext(mapper.apply(rs));
			} else {
				emitter.onComplete();
			}
		};
		Consumer<ResultSet> disposer = rs -> {
			try {
				rs.close();
			} catch (SQLException e) {
			}
		};
		return Flowable.generate(initialState, generator, disposer);
	}
	

}