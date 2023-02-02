package searchengine.models;

import lombok.*;
import org.hibernate.Hibernate;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.*;
import javax.persistence.Index;
import java.util.Objects;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "lemma", indexes = {
    @Index(
        name = "idx_lemma",
        columnList = "lemma"
    )
})
@Transactional
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "site_id", nullable = false)
    private long siteId;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String lemma;

    @Column(nullable = false)
    private int frequency;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Lemma lemma = (Lemma) o;
        return Objects.equals(id, lemma.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
