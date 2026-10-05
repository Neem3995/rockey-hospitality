package com.rockey.hospitality.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "rooms",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rooms_room_number",
                columnNames = "room_number"
        ),
        indexes = @Index(
                name = "idx_rooms_status_next_arrival",
                columnList = "status,next_arrival_at"
        )
)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Positive
    private Long id;

    @NotBlank
    @Size(max = 10)
    @Column(name = "room_number", nullable = false, length = 10)
    private String roomNumber;

    @NotBlank
    @Size(min = 2, max = 50)
    @Column(name = "room_type", nullable = false, length = 50)
    private String roomType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoomStatus status = RoomStatus.READY;

    @Min(1)
    @Max(99)
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer floor;

    @Column(name = "next_arrival_at")
    private LocalDateTime nextArrivalAt;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Room() {
    }

    public Room(
            String roomNumber,
            String roomType,
            Integer floor,
            RoomStatus status,
            LocalDateTime nextArrivalAt
    ) {
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.floor = floor;
        this.status = status == null ? RoomStatus.READY : status;
        this.nextArrivalAt = nextArrivalAt;
    }

    @PrePersist
    void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = RoomStatus.READY;
        }
        if (active == null) {
            active = true;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void prepareForUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void updateDetails(
            String roomType,
            Integer floor,
            LocalDateTime nextArrivalAt,
            Boolean active
    ) {
        this.roomType = roomType;
        this.floor = floor;
        this.nextArrivalAt = nextArrivalAt;
        this.active = active;
    }

    public void updateStatus(RoomStatus status) {
        this.status = status;
    }

    public void deactivate() {
        active = false;
    }

    public Long getId() {
        return id;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public String getRoomType() {
        return roomType;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public Integer getFloor() {
        return floor;
    }

    public LocalDateTime getNextArrivalAt() {
        return nextArrivalAt;
    }

    public Boolean getActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
