package com.fzu.addressbook.repository;

import com.fzu.addressbook.domain.Contact;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    @Override
    @EntityGraph(attributePaths = "methods")
    Optional<Contact> findById(Long id);

    @EntityGraph(attributePaths = "methods")
    List<Contact> findAllByOrderByFavoriteDescUpdatedAtDesc();

    @EntityGraph(attributePaths = "methods")
    List<Contact> findByFavoriteTrueOrderByUpdatedAtDesc();

    /** 导入时按姓名定位可能重复的联系人。 */
    @EntityGraph(attributePaths = "methods")
    List<Contact> findByName(String name);

    /**
     * 关键字搜索：命中姓名、分组、备注或任意一条联系方式的值。
     */
    @EntityGraph(attributePaths = "methods")
    @Query("""
            select distinct c from Contact c
            left join c.methods m
            where lower(c.name) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(c.group, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(c.note, '')) like lower(concat('%', :keyword, '%'))
               or lower(m.value) like lower(concat('%', :keyword, '%'))
            order by c.favorite desc, c.updatedAt desc
            """)
    List<Contact> search(@Param("keyword") String keyword);
}
