package com.example.reservation;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationService - zaawansowane mockowanie")
public class ReservationServiceTest {

    private static final String ROOM_ID = "room-101";
    private static final String ROOM_NAME = "Sala A";
    private static final int CAPACITY = 10;
    private static final int ATTENDEES = 8;
    private static final String ORGANIZER_EMAIL = "jan@firma.pl";
    private static final String CONFIRMATION_CODE = "RES-A1B2C3D4";
    private static final String EVENT_TYPE = "RESERVATION_CREATED";

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationEmailService emailService;

    @Mock
    private ConfirmationCodeGenerator codeGenerator;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ReservationService reservationService;

    @Captor
    private ArgumentCaptor<Reservation> reservationCaptor;

    @Captor
    private ArgumentCaptor<ConfirmationEmail> emailCaptor;

    @Captor
    private ArgumentCaptor<String> detailsCaptor;

    private Room room;
    private TimeSlot timeSlot;

    @BeforeEach
    void setUp() {
        room = new Room(ROOM_ID, ROOM_NAME, CAPACITY, true);
        timeSlot = new TimeSlot(LocalDate.of(2026, 10, 20),
                LocalTime.of(10, 0), LocalTime.of(12, 0));
    }

    private void givenRoomExistsAndSlotIsFree() {
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(reservationRepository.existsByRoomIdAndTimeSlot(ROOM_ID, timeSlot)).thenReturn(false);
        when(reservationRepository.save(any())).thenAnswer(i -> {
            Reservation reservation = i.getArgument(0);
            reservation.setRoomId(ROOM_ID);
            return reservation;
        });
    }

    private void givenReservationIsPossible() {
        givenRoomExistsAndSlotIsFree();
        when(codeGenerator.generate()).thenReturn(CONFIRMATION_CODE);
    }

    private Reservation whenCreateReservation() {
        return reservationService.createReservation(
                ROOM_ID, ORGANIZER_EMAIL, timeSlot, ATTENDEES);
    }

    @Nested
    @DisplayName("ArgumentCaptor - weryfikacja zapisanych danych")
    class ArgumentCaptorTests {

        @Test
        void savesReservationWithCorrectData() {
            givenReservationIsPossible();

            whenCreateReservation();

            verify(reservationRepository).save(reservationCaptor.capture());
            Reservation saved = reservationCaptor.getValue();
            assertThat(saved.getRoomId()).isEqualTo(ROOM_ID);
            assertThat(saved.getOrganizerEmail()).isEqualTo(ORGANIZER_EMAIL);
            assertThat(saved.getTimeSlot()).isEqualTo(timeSlot);
            assertThat(saved.getAttendees()).isEqualTo(ATTENDEES);
            assertThat(saved.getConfirmationCode()).isEqualTo(CONFIRMATION_CODE);
            assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        void sendsEmailWithCorrectData() {
            givenReservationIsPossible();

            whenCreateReservation();

            verify(emailService).sendConfirmation(emailCaptor.capture());
            ConfirmationEmail email = emailCaptor.getValue();
            assertThat(email.getRecipientEmail()).isEqualTo(ORGANIZER_EMAIL);
            assertThat(email.getSubject()).isEqualTo("Potwierdzenie rezerwacji sali " + ROOM_NAME);
            assertThat(email.getBody())
                    .contains(ROOM_NAME, CONFIRMATION_CODE, "2026-10-20", "10:00", "12:00");
        }

        @Test
        void publishesEventWithReservationData() {
            givenReservationIsPossible();

            whenCreateReservation();

            verify(eventPublisher).publish(eq(EVENT_TYPE), detailsCaptor.capture());
            assertThat(detailsCaptor.getValue()).contains(CONFIRMATION_CODE, ROOM_NAME);
        }
    }

    @Nested
    @DisplayName("InOrder - weryfikacja kolejności operacji")
    class InOrderTests {

        @Test
        void processesReservationWithCorrectData() {
            givenReservationIsPossible();
            whenCreateReservation();
            InOrder inOrder = Mockito.inOrder(roomRepository, reservationRepository,
                    codeGenerator, emailService, eventPublisher);
            inOrder.verify(roomRepository).findById(ROOM_ID);
            inOrder.verify(reservationRepository).existsByRoomIdAndTimeSlot(ROOM_ID, timeSlot);
            inOrder.verify(codeGenerator).generate();
            inOrder.verify(emailService).sendConfirmation(any(ConfirmationEmail.class));
            inOrder.verify(eventPublisher).publish(eq(EVENT_TYPE), contains(CONFIRMATION_CODE));
        }
    }

    @Nested
    @DisplayName("thenAnswer - dynamiczne generowanie wyników")
    class ThenAnswerTests {

        @Test
        void generatesUniqueCodesForConsecutiveReservations() {
            givenReservationIsPossible();
            AtomicInteger counter = new AtomicInteger();
            when(codeGenerator.generate())
                    .thenAnswer(i -> "RES-" + counter.incrementAndGet());

            Reservation first = whenCreateReservation();
            Reservation second = whenCreateReservation();

            assertThat(first.getConfirmationCode()).isEqualTo("RES-1");
            assertThat(second.getConfirmationCode()).isEqualTo("RES-2");
        }
    }

    @Nested
    @DisplayName("Walidacja - testy błędów")
    class ValidationTests {

        @Test
        void throwsExceptionForNonExistantRoom() {
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> whenCreateReservation())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Sala nie istnieje: " + ROOM_ID);

            verifyNoInteractions(reservationRepository, codeGenerator, emailService, eventPublisher);
        }

        @Test
        void throwsExceptionWhenTooManyAttendees() {
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            int tooManyAttendees = CAPACITY + 1;

            assertThatThrownBy(() -> reservationService.createReservation(
                    ROOM_ID, ORGANIZER_EMAIL, timeSlot, tooManyAttendees))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(
                            "pojemność " + CAPACITY + ", żądano " + tooManyAttendees);

            verifyNoInteractions(reservationRepository, codeGenerator, emailService, eventPublisher);
        }

        @Test
        void throwsWhenSlotAlreadyBooked() {
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(reservationRepository.existsByRoomIdAndTimeSlot(ROOM_ID, timeSlot)).thenReturn(true);

            assertThatThrownBy(() -> whenCreateReservation())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jest już zarezerwowana");

            verify(codeGenerator, never()).generate();
            verify(reservationRepository, never()).save(any());
            verifyNoInteractions(emailService, eventPublisher);

        }
    }
}
