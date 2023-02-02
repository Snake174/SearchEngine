package searchengine.models;

import lombok.*;
import org.hibernate.Hibernate;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.*;
import java.util.Objects;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "`index`")
@Transactional
public class Index {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "page_id", nullable = false)
    private long pageId;

    @Column(name = "lemma_id", nullable = false)
    private long lemmaId;

    @Column(nullable = false)
    private float rank;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Index index = (Index) o;
        return Objects.equals(id, index.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
