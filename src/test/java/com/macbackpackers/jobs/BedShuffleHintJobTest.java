package com.macbackpackers.jobs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.services.shuffle.BedCalendar;
import com.macbackpackers.services.shuffle.BedShuffleService;
import com.macbackpackers.services.shuffle.ShuffleBed;
import com.macbackpackers.services.shuffle.ShuffleBooking;
import com.macbackpackers.services.shuffle.ShuffleSuggestion;

public class BedShuffleHintJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of( 2026, 9, 28, 10, 5 );

    private final WordPressDAO dao = mock( WordPressDAO.class );
    private final BedShuffleService service = mock( BedShuffleService.class );

    @Test
    public void supersededReportIsSkipped() throws Exception {
        when( dao.isLatestSplitRoomReport( 5 ) ).thenReturn( false );
        job().processJob();
        verifyNoInteractions( service );
        verify( dao, never() ).updateSplitRoomShuffleHint( anyInt(), anyLong(), anyString(), any() );
    }

    @Test
    public void suggestionIsStoredOnReportRows() throws Exception {
        when( dao.isLatestSplitRoomReport( 5 ) ).thenReturn( true );
        when( service.suggestForReservation( eq( 1L ), any( BedShuffleService.Options.class ) ) ).thenReturn( found() );
        job().processJob();

        ArgumentCaptor<BedShuffleService.Options> options = ArgumentCaptor.forClass( BedShuffleService.Options.class );
        verify( service ).suggestForReservation( eq( 1L ), options.capture() );
        assertThat( ReflectionTestUtils.getField( options.getValue(), "consolidate" ), is( true ) );
        ArgumentCaptor<String> hint = ArgumentCaptor.forClass( String.class );
        verify( dao ).updateSplitRoomShuffleHint( eq( 5 ), eq( 1L ), eq( "FOUND" ), hint.capture() );
        assertThat( hint.getValue(), not( startsWith( "Reservation " ) ) );
        assertThat( hint.getValue(), containsString( "1. Assign 1 (Guest g) to A-1" ) );
    }

    @Test
    public void hintDropsHeaderAndAddsTimestamp() {
        String hint = BedShuffleHintJob.hintText( found(), NOW );
        assertThat( hint, startsWith( "1. Assign" ) );
        assertThat( hint, endsWith( "\n(Suggested 28-Sep 10:05; independent of other rows)" ) );
    }

    @Test
    public void nothingToDoHasNoHint() {
        BedCalendar calendar = calendar();
        calendar.addBooking( new ShuffleBooking( "g", 1L, "Guest g", 1, d( 1 ), d( 3 ), false ), "A1" );
        ShuffleSuggestion s = BedShuffleService.suggest( calendar, 1L );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.ALREADY_ASSIGNED ) );
        assertThat( BedShuffleHintJob.hintText( s, NOW ), nullValue() );
    }

    private BedShuffleHintJob job() {
        BedShuffleHintJob job = new BedShuffleHintJob();
        job.setAllocationScraperJobId( 5 );
        job.setReservationId( 1L );
        ReflectionTestUtils.setField( job, "dao", dao );
        ReflectionTestUtils.setField( job, "bedShuffleService", service );
        return job;
    }

    private static ShuffleSuggestion found() {
        BedCalendar calendar = calendar();
        calendar.addBooking( new ShuffleBooking( "g", 1L, "Guest g", 1, d( 1 ), d( 3 ), false ), null );
        ShuffleSuggestion s = BedShuffleService.suggest( calendar, 1L );
        assertThat( s.getStatus(), is( ShuffleSuggestion.Status.FOUND ) );
        return s;
    }

    private static BedCalendar calendar() {
        return new BedCalendar( List.of( new ShuffleBed( "A1", "A", "1", 1 ), new ShuffleBed( "A2", "A", "2", 1 ) ) );
    }

    private static LocalDate d( int dayOfOctober ) {
        return LocalDate.of( 2026, 10, dayOfOctober );
    }
}
