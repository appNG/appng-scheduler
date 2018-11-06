package org.appng.application.scheduler.business;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.appng.api.DataContainer;
import org.appng.api.DataProvider;
import org.appng.api.Environment;
import org.appng.api.FieldProcessor;
import org.appng.api.Options;
import org.appng.api.Request;
import org.appng.api.ScheduledJobResult;
import org.appng.api.ScheduledJobResult.ExecutionResult;
import org.appng.api.model.Application;
import org.appng.api.model.Site;
import org.appng.application.scheduler.model.JobRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class Records implements DataProvider {

	private DataSource dataSource;

	@Override
	public DataContainer getData(Site site, Application application, Environment environment, Options options,
			Request request, FieldProcessor fieldProcessor) {
		DataContainer dc = new DataContainer(fieldProcessor);
		List<JobRecord> records = getRecords(options, site);
		dc.setPage(records, fieldProcessor.getPageable());
		return dc;
	}

	private List<JobRecord> getRecords(Options options, Site site) {
		JdbcTemplate template = new JdbcTemplate(dataSource);
		Object[] args = { site.getName() };
		List<JobRecord> records = template.query(
				"SELECT site,application,job_name,duration,start,end,result FROM job_execution_record WHERE site = ? ORDER BY start DESC",
				args, new RowMapper<JobRecord>() {

					@Override
					public JobRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
						JobRecord record = new JobRecord();
						record.setSiteName(rs.getString("site"));
						record.setJobName(rs.getString("job_name"));
						record.setApplicationName(rs.getString("application"));
						record.setStart(rs.getTimestamp("start"));
						record.setEnd(rs.getTimestamp("end"));
						record.setDuration(rs.getLong("duration"));
						record.setScheduledJobResult(new ScheduledJobResult());
						record.getScheduledJobResult().setResult(ExecutionResult.valueOf(rs.getString("result")));
						return record;
					}
				});

		return records;
	}

	public DataSource getDataSource() {
		return dataSource;
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

}
