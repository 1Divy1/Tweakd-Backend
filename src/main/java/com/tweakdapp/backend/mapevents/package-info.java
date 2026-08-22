/**
 * The Map Events module owns <strong>car events</strong> — meets, and later other categories —
 * that appear as pins on the app's virtual map.
 *
 * <h2>Naming</h2>
 * The module is "map events" (it is a map feature); the Supabase tables it owns are named
 * {@code car_event_*} / {@code car_events} / {@code event_car_meet}. Types here carry the
 * {@code MapEvent} prefix and name their table explicitly.
 *
 * <h2>Lifecycle</h2>
 * A user creates an event; it starts {@code approval_status = 'pending'} and is invisible on the
 * map until an admin accepts it. Rejected events carry a {@code rejection_reason} the creator can
 * act on; editing a rejected event resubmits it. <strong>An accepted event is locked</strong> —
 * organizers can no longer edit it, only cancel it.
 *
 * <p>{@code status} ({@code upcoming} / {@code live} / {@code previous} / {@code hidden} /
 * {@code canceled}) is stored, never derived, and <strong>nothing sweeps it automatically</strong> —
 * automatic transitions were deliberately deferred. Organizers move an event to {@code previous}
 * ("mark finished") or {@code canceled} themselves. Because of that, read queries additionally
 * filter on time so a forgotten event does not sit on the map forever.
 *
 * <h2>Organizers</h2>
 * Exactly one user creates an event ({@code car_events.created_by}, immutable). That user also gets
 * a {@code car_event_organizers} row with {@code role = 'creator'}. Further organizers — individual
 * users <em>or</em> business accounts — can be added later with {@code role = 'organizer'}.
 * Business organizers are <strong>credit only</strong> for now: business accounts have no login
 * yet, so nobody can act as one.
 *
 * <h2>Attendees vs participants</h2>
 * <ul>
 *   <li><strong>Attendees</strong> ({@code car_event_attendees_list}) are spectators. Open RSVP —
 *       {@code attending} or {@code interested} — with no organizer approval. RSVP stays open while
 *       the event is running and closes only once it has finished or been canceled.</li>
 *   <li><strong>Participants</strong> ({@code car_event_participants}) are <em>cars</em> entered
 *       into the event by their owner, before the category's registration deadline. When the event
 *       has {@code requires_participant_approval} they start {@code pending} and an organizer
 *       accepts or rejects them.</li>
 * </ul>
 *
 * <h2>Subcategories</h2>
 * {@code car_events.event_type} points at {@code car_event_categories}; each category has its own
 * detail table. Today only {@code car_meet} exists ({@code event_car_meet}, carrying
 * {@code registration_deadline}); more are planned, as are contests.
 *
 * <h2>Main API</h2>
 * Public interface: {@link com.tweakdapp.backend.mapevents.MapEventsService}
 * <ul>
 *   <li><strong>Map:</strong> findNearby — radius search, nearest first, approved events only</li>
 *   <li><strong>Event page:</strong> getEvent, listAttendees, listParticipants</li>
 *   <li><strong>Authoring:</strong> create / update / cover image / cancel / finish / delete,
 *       organizer management</li>
 *   <li><strong>Taking part:</strong> setAttendance, registerCar, and the organizer's accept/reject</li>
 *   <li><strong>Admin:</strong> the pending queue plus approve / reject / delete, called by the
 *       {@code admin} module (which owns the dashboard endpoints and capability checks)</li>
 * </ul>
 *
 * <h2>Cross-module dependencies</h2>
 * {@code profile} (organizer / attendee identities), {@code garage} (participating cars and their
 * ownership), {@code business} (business co-organizers), {@code storage} (cover image keys →
 * public URLs), and {@code shared.geo} for lat/lng ↔ JTS {@code Point} conversion.
 */
@ApplicationModule(
        displayName = "Map Events"
)
package com.tweakdapp.backend.mapevents;

import org.springframework.modulith.ApplicationModule;
