package searchengine.models;

import lombok.*;
import org.hibernate.Hibernate;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.Index;
import javax.persistence.*;
import java.util.Objects;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "page", indexes = {
    @Index(
        name = "idx_path",
        columnList = "path"
    )
})
@Transactional
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "site_id", nullable = false, insertable = false, updatable = false)
    private long siteId;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;

    @ManyToOne
    @JoinColumn(name = "site_id")
    private Site site;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Page page = (Page) o;
        return Objects.equals(id, page.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
